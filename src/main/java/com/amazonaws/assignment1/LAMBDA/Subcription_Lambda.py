import json
import boto3
import uuid
import datetime
import logging

# Configure logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)

dynamodb = boto3.resource('dynamodb')
table = dynamodb.Table('Subscription')

def lambda_handler(event, context):
    logger.info(f"Received event: {json.dumps(event)}")

    method = event['httpMethod']
    path = event['path']

    logger.info(f"Processing {method} request to {path}")

    if method == 'POST' and path == '/subscriptions':
        return add_subscription(event)
    elif method == 'DELETE' and path.startswith('/subscriptions/'):
        return remove_subscription(event)
    elif method == 'GET' and path == '/subscriptions':
        return get_subscriptions(event)
    else:
        return {'statusCode': 400, 'body': json.dumps({'success': False, 'message': 'Invalid Request'})}

def add_subscription(event):
    try:
        body = json.loads(event['body'])
        logger.info(f"Processing subscription request: {json.dumps(body)}")

        # Get required fields - CHANGED 'email' to 'userEmail'
        userEmail = body.get('userEmail')
        if not userEmail:
            return {'statusCode': 400, 'body': json.dumps({'success': False, 'message': 'userEmail is required'})}

        title = body.get('title')
        artist = body.get('artist')

        if not title or not artist:
            return {'statusCode': 400, 'body': json.dumps({'success': False, 'message': 'Title and artist are required'})}

        # Optional fields
        year = body.get('year', '')
        album = body.get('album', '')

        # Check if subscription already exists - CHANGED Attr('email') to Attr('userEmail')
        existing_items = table.scan(
            FilterExpression=boto3.dynamodb.conditions.Attr('userEmail').eq(userEmail) &
                          boto3.dynamodb.conditions.Attr('title').eq(title) &
                          boto3.dynamodb.conditions.Attr('artist').eq(artist)
        )

        if existing_items.get('Items'):
            return {'statusCode': 400, 'body': json.dumps({
                'success': False,
                'message': f'Already subscribed to {title}'
            })}

        # Generate unique ID for subscription
        subscription_id = str(uuid.uuid4())

        # Create subscription item - CHANGED 'email' key to 'userEmail'
        item = {
            'id': subscription_id,
            'userEmail': userEmail,
            'title': title,
            'artist': artist,
            'timestamp': int(datetime.datetime.now().timestamp() * 1000)
        }

        # Add optional fields if present
        if year:
            item['year'] = year
        if album:
            item['album'] = album

        # Generate image URL
        imageKey = f"artist-images/{artist.replace(' ', '')}.jpg"
        # Note: Ensure your S3 bucket name is correct here
        item['imageUrl'] = f"https://s3.amazonaws.com/s4062787-mybucket/{imageKey}"

        logger.info(f"Adding subscription: {json.dumps(item)}")
        table.put_item(Item=item)

        return {'statusCode': 200, 'body': json.dumps({
            'success': True,
            'message': f'Successfully subscribed to {title}',
            'subscription': {
                'id': subscription_id,
                'title': title,
                'artist': artist,
                'year': year,
                'album': album,
                'imageUrl': item['imageUrl']
            }
        }, default=str)}
    except Exception as e:
        logger.error(f"Error adding subscription: {str(e)}")
        return {'statusCode': 500, 'body': json.dumps({
            'success': False,
            'message': f'Error: {str(e)}'
        })}

def remove_subscription(event):
    try:
        subscription_id = event['pathParameters']['id']
        logger.info(f"Removing subscription with ID: {subscription_id}")

        # Get subscription to verify ownership
        response = table.get_item(Key={'id': subscription_id})
        item = response.get('Item')

        if not item:
            return {'statusCode': 404, 'body': json.dumps({
                'success': False,
                'message': 'Subscription not found'
            })}

        # Check if userEmail matches from query params - CHANGED 'email' to 'userEmail'
        userEmail = event.get('queryStringParameters', {}).get('userEmail')
        # CHANGED item.get('email') to item.get('userEmail')
        if userEmail and userEmail != item.get('userEmail'):
            return {'statusCode': 403, 'body': json.dumps({
                'success': False,
                'message': "You don't have permission to remove this subscription"
            })}

        # Delete subscription
        table.delete_item(Key={'id': subscription_id})

        return {'statusCode': 200, 'body': json.dumps({
            'success': True,
            'message': 'Subscription removed successfully'
        })}
    except Exception as e:
        logger.error(f"Error removing subscription: {str(e)}")
        return {'statusCode': 500, 'body': json.dumps({
            'success': False,
            'message': f'Error: {str(e)}'
        })}

def get_subscriptions(event):
    try:
        # Get userEmail from query parameters - CHANGED 'email' to 'userEmail'
        userEmail = event.get('queryStringParameters', {}).get('userEmail')

        if not userEmail:
            return {'statusCode': 400, 'body': json.dumps({
                'success': False,
                # CHANGED error message
                'message': 'userEmail parameter is required'
            })}

        # CHANGED variable name in log message
        logger.info(f"Getting subscriptions for userEmail: {userEmail}")

        # Query subscriptions using scan with filter - CHANGED Attr('email') to Attr('userEmail')
        response = table.scan(
            FilterExpression=boto3.dynamodb.conditions.Attr('userEmail').eq(userEmail)
        )

        return {'statusCode': 200, 'body': json.dumps({
            'success': True,
            'subscriptions': response['Items']
        }, default=str)}
    except Exception as e:
        logger.error(f"Error getting subscriptions: {str(e)}")
        return {'statusCode': 500, 'body': json.dumps({
            'success': False,
            'message': f'Error: {str(e)}'
        })}

